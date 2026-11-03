package com.blinkng.shared

/**
 * Platform-neutral visual contract for Blink Store cosmetics.
 *
 * Charging, ownership, activation and expiry remain server-authoritative. This catalog only
 * describes how an already-authorized cosmetic should be presented consistently on Android,
 * Windows and the web parity client.
 */
enum class BlinkPremiumSurface {
    PROFILE_AURA,
    PROFILE_THEME,
    AVATAR_FRAME,
    NAME_SIGNATURE,
    PROFILE_BADGE,
    PROFILE_ENTRANCE,
    COMMENT_SPOTLIGHT,
    POST_SIGNATURE,
    CHAT_STYLE,
    REACTION_EFFECT,
    COLLECTION,
    UTILITY,
}

enum class BlinkPremiumMotion {
    LIGHT_SWEEP,
    AURA_PULSE,
    EDGE_REVEAL,
    ORBIT,
    GRADIENT_DRIFT,
    SPRING_REVEAL,
    PARTICLE_FLOAT,
    REACTION_BURST,
    STATIC,
}

data class BlinkPremiumVisualSpec(
    val catalogId: String,
    val displayName: String,
    val signatureLabel: String,
    val surface: BlinkPremiumSurface,
    /** 24-bit RGB values so every client can construct its own native color type. */
    val primaryRgb: Int,
    val secondaryRgb: Int,
    val tertiaryRgb: Int,
    val motion: BlinkPremiumMotion,
    val priority: Int,
    val fullSurface: Boolean = false,
)

data class BlinkPremiumProfileLayers(
    val aura: BlinkPremiumVisualSpec? = null,
    val theme: BlinkPremiumVisualSpec? = null,
    val frame: BlinkPremiumVisualSpec? = null,
    val name: BlinkPremiumVisualSpec? = null,
    val badge: BlinkPremiumVisualSpec? = null,
    val entrance: BlinkPremiumVisualSpec? = null,
) {
    val strongest: BlinkPremiumVisualSpec?
        get() = listOfNotNull(aura, theme, frame, name, badge, entrance)
            .maxByOrNull(BlinkPremiumVisualSpec::priority)

    val hasVisibleProfileEffect: Boolean
        get() = strongest != null
}

object BlinkPremiumCosmetics {
    private const val PURPLE = 0x7C3AED
    private const val PINK = 0xEC4899
    private const val BLUE = 0x2563EB
    private const val CYAN = 0x06B6D4
    private const val GOLD = 0xF59E0B
    private const val ORANGE = 0xF97316
    private const val RED = 0xEF4444
    private const val INK = 0x111827

    fun spec(catalogId: String): BlinkPremiumVisualSpec {
        val id = catalogId.trim().lowercase()
        return when (id) {
            "profile_highlight_1h" -> visual(id, "Profile Aura", "AURA", BlinkPremiumSurface.PROFILE_AURA, PURPLE, PINK, GOLD, BlinkPremiumMotion.LIGHT_SWEEP, 92, true)
            "profile_glow_1h" -> visual(id, "Neon Profile Aura", "NEON", BlinkPremiumSurface.PROFILE_AURA, PURPLE, PINK, BLUE, BlinkPremiumMotion.AURA_PULSE, 80, true)
            "profile_glow_7d" -> visual(id, "Seven-Day Aurora", "AURORA", BlinkPremiumSurface.PROFILE_AURA, CYAN, PURPLE, PINK, BlinkPremiumMotion.AURA_PULSE, 96, true)
            "profile_spotlight_1h", "profile_spotlight_24h" -> visual(id, "Profile Spotlight", "SPOTLIGHT", BlinkPremiumSurface.PROFILE_AURA, GOLD, PINK, PURPLE, BlinkPremiumMotion.LIGHT_SWEEP, if (id.endsWith("24h")) 97 else 88, true)
            "birthday_profile_theme" -> visual(id, "Celebration Aura", "CELEBRATE", BlinkPremiumSurface.PROFILE_AURA, PINK, GOLD, CYAN, BlinkPremiumMotion.PARTICLE_FLOAT, 86, true)

            "profile_background" -> visual(id, "Midnight Profile Theme", "MIDNIGHT", BlinkPremiumSurface.PROFILE_THEME, INK, PURPLE, BLUE, BlinkPremiumMotion.GRADIENT_DRIFT, 82, true)
            "profile_theme_3d" -> visual(id, "Prism Profile Theme", "PRISM", BlinkPremiumSurface.PROFILE_THEME, PURPLE, BLUE, CYAN, BlinkPremiumMotion.GRADIENT_DRIFT, 90, true)
            "profile_theme_bundle" -> visual(id, "Signature Profile Theme", "SIGNATURE", BlinkPremiumSurface.PROFILE_THEME, BLUE, PURPLE, PINK, BlinkPremiumMotion.GRADIENT_DRIFT, 94, true)
            "profile_banner" -> visual(id, "Premium Profile Banner", "BANNER", BlinkPremiumSurface.PROFILE_THEME, INK, PURPLE, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 91, true)
            "profile_particle_effect" -> visual(id, "Cosmic Profile Atmosphere", "COSMIC", BlinkPremiumSurface.PROFILE_THEME, PURPLE, CYAN, PINK, BlinkPremiumMotion.PARTICLE_FLOAT, 93, true)

            "profile_ring" -> visual(id, "Chrome Avatar Ring", "CHROME", BlinkPremiumSurface.AVATAR_FRAME, BLUE, PURPLE, CYAN, BlinkPremiumMotion.STATIC, 72)
            "animated_profile_ring" -> visual(id, "Orbit Avatar Ring", "ORBIT", BlinkPremiumSurface.AVATAR_FRAME, CYAN, PURPLE, PINK, BlinkPremiumMotion.ORBIT, 88)
            "premium_profile_frame" -> visual(id, "Prestige Avatar Frame", "PRESTIGE", BlinkPremiumSurface.AVATAR_FRAME, GOLD, PINK, PURPLE, BlinkPremiumMotion.LIGHT_SWEEP, 98)
            "avatar_decoration" -> visual(id, "Floating Avatar Accent", "AVATAR FX", BlinkPremiumSurface.AVATAR_FRAME, PINK, PURPLE, CYAN, BlinkPremiumMotion.ORBIT, 95)

            "username_glow_24h" -> visual(id, "Luminous Name", "LUMINOUS", BlinkPremiumSurface.NAME_SIGNATURE, PURPLE, PINK, CYAN, BlinkPremiumMotion.LIGHT_SWEEP, 84)
            "username_font" -> visual(id, "Signature Name", "SIGNATURE", BlinkPremiumSurface.NAME_SIGNATURE, BLUE, PURPLE, PINK, BlinkPremiumMotion.STATIC, 78)
            "animated_name" -> visual(id, "Living Name", "LIVE NAME", BlinkPremiumSurface.NAME_SIGNATURE, CYAN, PURPLE, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 93)

            "custom_profile_badge" -> visual(id, "Premium Identity Crest", "PREMIUM", BlinkPremiumSurface.PROFILE_BADGE, PURPLE, PINK, GOLD, BlinkPremiumMotion.SPRING_REVEAL, 82)
            "creator_badge" -> visual(id, "Creator Crest", "CREATOR", BlinkPremiumSurface.PROFILE_BADGE, GOLD, ORANGE, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 96)
            "limited_edition_badge" -> visual(id, "Limited Crest", "LIMITED", BlinkPremiumSurface.PROFILE_BADGE, GOLD, RED, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 99)

            "profile_entrance_animation" -> visual(id, "Profile Reveal", "REVEAL", BlinkPremiumSurface.PROFILE_ENTRANCE, PURPLE, PINK, BLUE, BlinkPremiumMotion.SPRING_REVEAL, 76)
            "creator_intro_card" -> visual(id, "Creator Entrance", "INTRO", BlinkPremiumSurface.PROFILE_ENTRANCE, GOLD, PINK, PURPLE, BlinkPremiumMotion.SPRING_REVEAL, 91)
            "vip_profile_entrance" -> visual(id, "VIP Entrance", "VIP", BlinkPremiumSurface.PROFILE_ENTRANCE, GOLD, PURPLE, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 100)

            "comment_highlight" -> visual(id, "Comment Spotlight", "SPOTLIGHT", BlinkPremiumSurface.COMMENT_SPOTLIGHT, PINK, PURPLE, GOLD, BlinkPremiumMotion.EDGE_REVEAL, 96, true)
            "comment_color" -> visual(id, "Aurora Comment Style", "AURORA", BlinkPremiumSurface.COMMENT_SPOTLIGHT, PURPLE, BLUE, CYAN, BlinkPremiumMotion.GRADIENT_DRIFT, 78, true)
            "comment_entrance_animation" -> visual(id, "Comment Premiere", "PREMIERE", BlinkPremiumSurface.COMMENT_SPOTLIGHT, CYAN, PURPLE, PINK, BlinkPremiumMotion.SPRING_REVEAL, 92, true)
            "vip_comment_effect" -> visual(id, "VIP Comment Signature", "VIP", BlinkPremiumSurface.COMMENT_SPOTLIGHT, GOLD, PINK, PURPLE, BlinkPremiumMotion.LIGHT_SWEEP, 100, true)

            "post_border", "post_highlight_1h", "post_spotlight_6h", "post_spotlight_24h", "post_entrance_animation", "premium_poll_style" ->
                visual(id, "Premium Post Signature", "POST FX", BlinkPremiumSurface.POST_SIGNATURE, PURPLE, PINK, GOLD, BlinkPremiumMotion.EDGE_REVEAL, 72, true)
            "reel_highlight_1h", "reel_spotlight_6h", "reel_spotlight_24h", "reel_frame_effect" ->
                visual(id, "Premium Reel Signature", "REEL FX", BlinkPremiumSurface.POST_SIGNATURE, PINK, PURPLE, CYAN, BlinkPremiumMotion.EDGE_REVEAL, 72, true)

            "chat_bubble_theme", "chat_background", "special_dm_theme" ->
                visual(id, "Premium Conversation Style", "CHAT", BlinkPremiumSurface.CHAT_STYLE, BLUE, PURPLE, PINK, BlinkPremiumMotion.GRADIENT_DRIFT, 68, true)
            "reaction_pack", "emoji_pack", "sticker_pack", "super_reaction", "vip_reaction_pack", "animated_like", "follow_animation" ->
                visual(id, "Premium Social Effect", "SOCIAL FX", BlinkPremiumSurface.REACTION_EFFECT, PINK, PURPLE, GOLD, BlinkPremiumMotion.REACTION_BURST, 64)

            "gift_crown", "gift_rose", "gift_trophy", "gift_galaxy", "digital_gift" ->
                visual(id, "Blink Collectible", "COLLECTIBLE", BlinkPremiumSurface.COLLECTION, GOLD, PINK, PURPLE, BlinkPremiumMotion.PARTICLE_FLOAT, 60)

            "blink_vip_10d", "vip_theme" ->
                visual(id, "Blink VIP Signature", "VIP", BlinkPremiumSurface.PROFILE_AURA, GOLD, PURPLE, PINK, BlinkPremiumMotion.LIGHT_SWEEP, 100, true)

            else -> visual(
                catalogId = id,
                displayName = id.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Blink Premium" },
                signatureLabel = "PREMIUM",
                surface = BlinkPremiumSurface.UTILITY,
                primaryRgb = PURPLE,
                secondaryRgb = PINK,
                tertiaryRgb = BLUE,
                motion = BlinkPremiumMotion.STATIC,
                priority = 40,
            )
        }
    }

    fun profileLayers(catalogIds: Collection<String>, isVip: Boolean = false): BlinkPremiumProfileLayers {
        val visuals = catalogIds.map(::spec).toMutableList()
        if (isVip && visuals.none { it.catalogId == "blink_vip_10d" }) visuals += spec("blink_vip_10d")

        fun strongest(surface: BlinkPremiumSurface) = visuals
            .asSequence()
            .filter { it.surface == surface }
            .maxByOrNull(BlinkPremiumVisualSpec::priority)

        return BlinkPremiumProfileLayers(
            aura = strongest(BlinkPremiumSurface.PROFILE_AURA),
            theme = strongest(BlinkPremiumSurface.PROFILE_THEME),
            frame = strongest(BlinkPremiumSurface.AVATAR_FRAME),
            name = strongest(BlinkPremiumSurface.NAME_SIGNATURE),
            badge = strongest(BlinkPremiumSurface.PROFILE_BADGE),
            entrance = strongest(BlinkPremiumSurface.PROFILE_ENTRANCE),
        )
    }

    fun strongestComment(catalogIds: Collection<String>): BlinkPremiumVisualSpec? = catalogIds
        .map(::spec)
        .filter { it.surface == BlinkPremiumSurface.COMMENT_SPOTLIGHT }
        .maxByOrNull(BlinkPremiumVisualSpec::priority)

    fun equipmentSlot(catalogId: String): String = when (catalogId) {
        "profile_ring", "animated_profile_ring", "premium_profile_frame" -> "profile_frame"
        "profile_background", "profile_theme_bundle" -> "profile_theme"
        "username_font", "animated_name" -> "name_style"
        "custom_profile_badge", "creator_badge", "limited_edition_badge" -> "profile_badge"
        "profile_entrance_animation", "vip_profile_entrance" -> "profile_entrance"
        "chat_bubble_theme", "special_dm_theme" -> "chat_theme"
        else -> catalogId
    }

    private fun visual(
        catalogId: String,
        displayName: String,
        signatureLabel: String,
        surface: BlinkPremiumSurface,
        primaryRgb: Int,
        secondaryRgb: Int,
        tertiaryRgb: Int,
        motion: BlinkPremiumMotion,
        priority: Int,
        fullSurface: Boolean = false,
    ) = BlinkPremiumVisualSpec(
        catalogId = catalogId,
        displayName = displayName,
        signatureLabel = signatureLabel,
        surface = surface,
        primaryRgb = primaryRgb,
        secondaryRgb = secondaryRgb,
        tertiaryRgb = tertiaryRgb,
        motion = motion,
        priority = priority,
        fullSurface = fullSurface,
    )
}

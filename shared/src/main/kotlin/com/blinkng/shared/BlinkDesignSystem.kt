package com.blinkng.shared

/**
 * Platform-neutral Blink visual contract.
 *
 * Android and Windows both compile this source set. Keep brand, semantic, motion,
 * shape and spacing decisions here so the two clients cannot silently drift apart.
 * Platform Compose themes convert the ARGB values below to their own Color objects.
 */
enum class BlinkAppearanceMode(val persistedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromPersisted(value: String?, fallbackDark: Boolean = true): BlinkAppearanceMode {
            return entries.firstOrNull { it.persistedValue == value?.trim()?.lowercase() }
                ?: if (fallbackDark) DARK else LIGHT
        }
    }
}

object BlinkDesignTokens {
    object Brand {
        const val Primary: Long = 0xFF8B5CF6L
        const val PrimaryBright: Long = 0xFFA66CFFL
        const val PrimaryDeep: Long = 0xFF6D28D9L
        const val Lavender: Long = 0xFFC4B5FDL
        const val Blue: Long = 0xFF3B82F6L
        const val Cyan: Long = 0xFF35C7E8L
    }

    object Dark {
        const val Background: Long = 0xFF090A0FL
        const val Surface: Long = 0xFF11131AL
        const val SurfaceElevated: Long = 0xFF171A22L
        const val Input: Long = 0xFF151820L
        const val SurfaceHighest: Long = 0xFF1D2029L
        const val Border: Long = 0xFF272B36L
        const val BorderSoft: Long = 0xFF20232DL
        const val TextPrimary: Long = 0xFFF7F7FAL
        const val TextSecondary: Long = 0xFFA7ABB8L
        const val TextMuted: Long = 0xFF707583L
    }

    object Light {
        const val Background: Long = 0xFFF7F7FBL
        const val Surface: Long = 0xFFFFFFFFL
        const val SurfaceElevated: Long = 0xFFFFFFFFL
        const val Input: Long = 0xFFF0F1F6L
        const val SurfaceHighest: Long = 0xFFE9EAF1L
        const val Border: Long = 0xFFDFE1E8L
        const val BorderSoft: Long = 0xFFEAEBF1L
        const val TextPrimary: Long = 0xFF161820L
        const val TextSecondary: Long = 0xFF616675L
        const val TextMuted: Long = 0xFF878C99L
    }

    object Semantic {
        const val Success: Long = 0xFF22C55EL
        const val Warning: Long = 0xFFF59E0BL
        const val Error: Long = 0xFFFF5D73L
        const val Info: Long = 0xFF3B82F6L
        const val Gold: Long = 0xFFF5C451L
    }

    /** Values are dp/sp-independent so they can be mapped by each client. */
    object Spacing {
        const val Xxs = 4
        const val Xs = 8
        const val Sm = 12
        const val Md = 16
        const val Lg = 20
        const val Xl = 24
        const val Xxl = 32
    }

    object Shape {
        const val Small = 12
        const val Medium = 16
        const val Large = 20
        const val ExtraLarge = 28
        const val Pill = 999
    }

    object Motion {
        const val Fast = 150
        const val Standard = 220
        const val Emphasized = 300
        const val Slow = 420
        const val PressedScale = 0.97f
    }
}

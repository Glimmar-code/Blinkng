package com.example.ui.theme

/**
 * Appearance is an explicit three-way preference, not a boolean. Keep legacy
 * users' stored light/dark choice without changing their settings on upgrade.
 */
enum class BlinkAppearanceMode {
    LIGHT, DARK, SYSTEM;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun restored(raw: String?, legacyDark: Boolean?): BlinkAppearanceMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: when (legacyDark) {
                    true -> DARK
                    false -> LIGHT
                    null -> LIGHT // New users start in Light Mode.
                }
    }
}

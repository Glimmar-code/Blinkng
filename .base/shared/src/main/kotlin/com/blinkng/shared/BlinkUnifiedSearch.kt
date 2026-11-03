package com.blinkng.shared

/**
 * Cross-platform contract for the unified Blink Search surface.
 *
 * UNIVERSAL keeps the Phase 3 server-authoritative discovery stack.
 * ADVANCED preserves the Phase 1 power-search tools.
 * EXPLORE preserves the Phase 2 Search / Trending / Places collections.
 */
enum class BlinkUnifiedSearchMode(val label: String) {
    UNIVERSAL("Universal"),
    ADVANCED("Advanced"),
    EXPLORE("Explore"),
}

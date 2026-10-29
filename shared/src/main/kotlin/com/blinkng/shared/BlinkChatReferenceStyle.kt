package com.blinkng.shared

/**
 * Visual contract for the supplied 29 Oct 2026 BLINK conversation reference.
 * Shared between Android and Windows, without changing message semantics.
 *
 * Reference is a near-black/charcoal chat with pale outgoing bubbles.
 * Keep the blue for actionable affordances, presence, and read receipts.
 */
object BlinkChatReferenceStyle {
    const val CanvasTop: Long = 0xFF080D11
    const val Canvas: Long = 0xFF090D10
    const val CanvasBottom: Long = 0xFF050A0E
    const val Header: Long = 0xFF090E12
    const val Input: Long = 0xFF202328
    const val Elevated: Long = 0xFF24282B
    const val Incoming: Long = 0xFF23272A
    const val Outgoing: Long = 0xFFEDEFF2
    const val OutgoingText: Long = 0xFF1A2026
    const val Blue: Long = 0xFF0879F8
    const val BlueBright: Long = 0xFF1685FF
    const val Text: Long = 0xFFF5F7FA
    const val Subtle: Long = 0xFFB5B8C0
    const val Muted: Long = 0xFF8D929A
    const val Border: Long = 0xFF32383E
}

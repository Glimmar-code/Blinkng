package com.blinkng.shared

/**
 * Reversible presentation gate for BLINK Store across all clients.
 *
 * Never deletes catalog, inventory, purchases, premium effects, or wallet state.
 * Set [isPaused] to false after Store is ready for public release.
 */
object BlinkStoreAvailability {
    const val isPaused: Boolean = true
    const val title = "Something better is coming"
    const val message = "BLINK Store is getting a refresh. New premium items and purchases are temporarily paused. Your Blink Coins and existing collection remain in your account."
    const val dismissHint = "Tap outside or press Back to continue"
}

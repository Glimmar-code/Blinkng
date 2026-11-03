package com.blinkng.shared

/** Protects reading position when new chat content arrives. */
enum class BlinkMessageScrollAction { KEEP, JUMP_TO_LATEST, ANIMATE_TO_LATEST, SHOW_NEW_MESSAGE }

object BlinkMessageScrollPolicy {
    fun decide(
        hasMessages: Boolean,
        filtering: Boolean,
        hasSeenLatest: Boolean,
        userHasScrolled: Boolean,
        nearLatest: Boolean,
        latestChanged: Boolean
    ): BlinkMessageScrollAction = when {
        !hasMessages || filtering -> BlinkMessageScrollAction.KEEP
        !hasSeenLatest || !userHasScrolled -> BlinkMessageScrollAction.JUMP_TO_LATEST
        !latestChanged -> BlinkMessageScrollAction.KEEP
        nearLatest -> BlinkMessageScrollAction.ANIMATE_TO_LATEST
        else -> BlinkMessageScrollAction.SHOW_NEW_MESSAGE
    }
}

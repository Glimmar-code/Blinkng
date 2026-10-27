package com.blinkng.shared

import java.util.Locale

/**
 * Cross-platform preparation/validation for native single-choice chat polls.
 *
 * The live backend MUST independently enforce every limit and membership check.
 * This class never grants message-send rights or treats a locally recorded vote
 * as accepted before the server acknowledges it.
 */
data class BlinkChatPollDraft(val question: String, val options: List<String>)

object BlinkChatPollPolicy {
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 8
    const val MAX_QUESTION_LENGTH = 200
    const val MAX_OPTION_LENGTH = 80

    fun prepare(question: String, options: List<String>): BlinkChatPollDraft {
        val cleanQuestion = question.trim()
        require(cleanQuestion.isNotEmpty() && cleanQuestion.length <= MAX_QUESTION_LENGTH) {
            "Poll questions must contain 1 to 200 characters."
        }
        require(options.size in MIN_OPTIONS..MAX_OPTIONS) {
            "Choose between 2 and 8 poll options."
        }
        val cleanOptions = options.map(String::trim)
        require(cleanOptions.all { it.isNotEmpty() && it.length <= MAX_OPTION_LENGTH }) {
            "Each poll option must contain 1 to 80 characters."
        }
        require(cleanOptions.map { it.lowercase(Locale.ROOT) }.distinct().size == cleanOptions.size) {
            "Poll options must be different."
        }
        return BlinkChatPollDraft(cleanQuestion, cleanOptions)
    }

    /**
     * Stable readable fallback for app versions without native poll rendering.
     * Never emit it for an invalid draft: callers must prepare() first.
     */
    fun textFallback(draft: BlinkChatPollDraft): String {
        val checked = prepare(draft.question, draft.options)
        return buildString {
            append("📊 Poll question: ").append(checked.question).append('\n')
            checked.options.forEachIndexed { index, option ->
                append(index + 1).append(". ").append(option).append('\n')
            }
            append("(Reply with your choice number)")
        }
    }

    fun validChoice(choiceIndex: Int, optionCount: Int): Boolean =
        optionCount in MIN_OPTIONS..MAX_OPTIONS && choiceIndex in 0 until optionCount

    /** No optimistic voting or results are shown as confirmed until the RPC returns. */
    fun canVote(
        isCurrentParticipant: Boolean,
        isDeletedForEveryone: Boolean,
        isClosed: Boolean
    ): Boolean = isCurrentParticipant && !isDeletedForEveryone && !isClosed
}

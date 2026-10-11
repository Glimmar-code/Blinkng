package com.blinkng.shared

/** Presentation rules shared by Android and Windows; never expose server exception text. */
object BlinkUiRecovery {
    fun profileSaveMessage(error: Throwable): String = when {
        generateSequence(error) { it.cause }.take(8).any {
            it is java.io.IOException
        } -> "Couldn't save your profile. Check your connection and try again."
        else -> "Couldn't save your profile. Your changes are still here; please try again."
    }

    fun priceRangeError(minimum: String, maximum: String): String? {
        val min = minimum.takeIf { it.isNotBlank() }?.toLongOrNull()
        val max = maximum.takeIf { it.isNotBlank() }?.toLongOrNull()
        return when {
            (minimum.isNotBlank() && min == null) || (maximum.isNotBlank() && max == null) ->
                "Enter a valid price."
            (min != null && min < 0) || (max != null && max < 0) -> "Prices cannot be negative."
            min != null && max != null && min > max -> "Minimum price must not exceed maximum price."
            else -> null
        }
    }

    fun matchesPrice(price: Long, minimum: String, maximum: String): Boolean =
        priceRangeError(minimum, maximum) == null &&
            (minimum.toLongOrNull()?.let { price >= it } ?: true) &&
            (maximum.toLongOrNull()?.let { price <= it } ?: true)

    fun messageScrollIndex(ids: List<String>, target: String, hasLoadingRow: Boolean): Int? =
        ids.indexOf(target).takeIf { it >= 0 }?.let { it + if (hasLoadingRow) 1 else 0 }
}

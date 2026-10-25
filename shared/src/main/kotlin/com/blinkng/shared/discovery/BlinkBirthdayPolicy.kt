package com.blinkng.shared.discovery

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Birth dates are private account data; never add this value to referral metadata. */
object BlinkBirthdayPolicy {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)

    fun isValid(value: String, today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { LocalDate.parse(value.trim(), format) }.getOrNull() ?: return false
        return !date.isAfter(today) && !date.isBefore(today.minusYears(120))
    }
}

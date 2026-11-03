package com.blinkng.shared

/**
 * A display-only contract shared by Android and Windows.
 * Server receipts are authoritative: device connectivity/presence alone
 * must never promote a message to DELIVERED or READ.
 */
object BlinkDeliveryReceipt {
    fun rank(status: String): Int = when (status.uppercase()) {
        "READ" -> 3
        "DELIVERED" -> 2
        "SENT" -> 1
        "SENDING" -> 0
        else -> -1
    }

    fun strongest(previous: String, latest: String): String =
        if (rank(latest) >= rank(previous)) latest else previous

    fun ticks(status: String): String = when (status.uppercase()) {
        "SENDING" -> "◷"
        "FAILED" -> "!"
        "SENT" -> "✓"
        "DELIVERED", "READ" -> "✓✓"
        else -> ""
    }

    fun isRead(status: String): Boolean = status.equals("READ", ignoreCase = true)
}

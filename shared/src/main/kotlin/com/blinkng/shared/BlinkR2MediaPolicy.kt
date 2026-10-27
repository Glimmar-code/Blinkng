package com.blinkng.shared

/**
 * Client-independent R2 rollout contract. Secrets and signing live only in
 * Supabase Edge Functions. Legacy Supabase Storage remains the safe fallback
 * only when the signer has intentionally disabled the R2 feature.
 */
object BlinkR2MediaPolicy {
    private val types: Map<String, Set<String>> = mapOf(
        "feed" to setOf("image/jpeg","image/png","image/webp","image/heic"),
        "reel" to setOf("video/mp4","video/webm"),
        "profile" to setOf("image/jpeg","image/png","image/webp"),
        "market" to setOf("image/jpeg","image/png","image/webp","video/mp4","video/webm"),
    )
    private val limits = mapOf(
        "feed" to 25_000_000L,
        "reel" to 80_000_000L,
        "profile" to 8_000_000L,
        "market" to 50_000_000L,
    )

    fun mayUploadPublic(purpose: String, mime: String, bytes: Long): Boolean =
        bytes > 0L && bytes <= (limits[purpose] ?: 0L) &&
            types[purpose]?.contains(mime.lowercase()) == true

    fun intentionalFallback(httpStatus: Int, serverError: String): Boolean =
        (httpStatus == 503 && serverError == "R2_DISABLED") ||
            (httpStatus == 404 && serverError == "Function not found")

    /** Reject mixed-content, signed S3 endpoints and unexpected external URLs. */
    fun isValidPublicUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            !uri.host.isNullOrBlank() &&
            (uri.host.equals("blink.com.ng", true) ||
                uri.host.endsWith(".blink.com.ng", true)) &&
            uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)
}

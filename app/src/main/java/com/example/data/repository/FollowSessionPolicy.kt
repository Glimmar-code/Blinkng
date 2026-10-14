package com.example.data.repository

/** Guard against a completed network response being applied after a session swap. */
internal fun isCurrentFollowSession(requestToken: String?, currentToken: String?): Boolean =
    !requestToken.isNullOrBlank() && requestToken == currentToken

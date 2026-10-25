package com.example.ui.screens

import android.content.Context

/**
 * An informational, dismissible one-time guide shown only when a person
 * successfully completes onboarding. Never authorizes checkout or auto-renewal.
 * The actual Blue/Gold, Coin and Boost screens enforce live server policy.
 */
internal object NewMemberPerksStore {
    private const val PREFS = "blink_new_member_perks_v1"

    fun shouldShow(context: Context, accountId: String): Boolean =
        accountId.isNotBlank() &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(accountId, false)

    fun markSeen(context: Context, accountId: String) {
        if (accountId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(accountId, true).apply()
    }
}

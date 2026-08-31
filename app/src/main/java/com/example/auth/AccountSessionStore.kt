package com.example.auth

import android.content.Context
import com.example.data.supabase.SupabaseService
import org.json.JSONArray
import org.json.JSONObject

/** Stores recent Supabase sessions without storing passwords. */
object AccountSessionStore {
    private const val PREFS = "blink_recent_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val MAX_ACCOUNTS = 5

    data class Account(
        val userId: String,
        val username: String,
        val fullName: String,
        val email: String,
        val avatarUrl: String,
        val accessToken: String,
        val refreshToken: String,
        val lastUsedAt: Long
    )

    fun recordCurrentSession(
        context: Context,
        userId: String,
        username: String,
        fullName: String,
        email: String,
        avatarUrl: String
    ) {
        val access = SupabaseService.accessToken().orEmpty()
        val refresh = SupabaseService.refreshToken().orEmpty()
        if (userId.isBlank() || username.isBlank() || access.isBlank() || refresh.isBlank()) return

        val updated = load(context)
            .filterNot { it.userId == userId }
            .toMutableList()
            .apply {
                add(
                    0,
                    Account(
                        userId,
                        username,
                        fullName,
                        email,
                        avatarUrl,
                        access,
                        refresh,
                        System.currentTimeMillis()
                    )
                )
            }
            .take(MAX_ACCOUNTS)
        save(context, updated)
    }

    fun list(context: Context): List<Account> =
        load(context).sortedByDescending { it.lastUsedAt }

    fun switchTo(context: Context, account: Account) {
        SupabaseService.saveSession(account.accessToken, account.refreshToken)
        val reordered = load(context)
            .filterNot { it.userId == account.userId }
            .toMutableList()
            .apply { add(0, account.copy(lastUsedAt = System.currentTimeMillis())) }
        save(context, reordered.take(MAX_ACCOUNTS))
    }

    private fun load(context: Context): List<Account> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        Account(
                            userId = o.optString("userId"),
                            username = o.optString("username"),
                            fullName = o.optString("fullName"),
                            email = o.optString("email"),
                            avatarUrl = o.optString("avatarUrl"),
                            accessToken = o.optString("accessToken"),
                            refreshToken = o.optString("refreshToken"),
                            lastUsedAt = o.optLong("lastUsedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { a ->
            array.put(
                JSONObject().apply {
                    put("userId", a.userId)
                    put("username", a.username)
                    put("fullName", a.fullName)
                    put("email", a.email)
                    put("avatarUrl", a.avatarUrl)
                    put("accessToken", a.accessToken)
                    put("refreshToken", a.refreshToken)
                    put("lastUsedAt", a.lastUsedAt)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNTS, array.toString())
            .apply()
    }
}

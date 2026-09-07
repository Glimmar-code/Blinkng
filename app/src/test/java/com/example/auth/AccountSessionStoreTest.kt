package com.example.auth

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountSessionStoreTest {
    @Test
    fun persistSelectedAccount_updatesBothLocalAuthStores() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("blink_user_session", Context.MODE_PRIVATE).edit().clear().commit()

        val account = AccountSessionStore.Account(
            userId = "11111111-1111-1111-1111-111111111111",
            username = "new_user",
            fullName = "New User",
            email = "new@example.com",
            avatarUrl = "https://example.com/avatar.jpg",
            accessToken = "access",
            refreshToken = "refresh",
            lastUsedAt = 1L
        )

        AccountSessionStore.persistSelectedAccount(context, account)

        listOf("blink_auth_prefs", "blink_user_session").forEach { prefsName ->
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            assertTrue(prefs.getBoolean("is_logged_in", false))
            assertEquals("new@example.com", prefs.getString("email", null))
            assertEquals("new_user", prefs.getString("username", null))
            assertEquals("New User", prefs.getString("full_name", null))
        }
    }
}

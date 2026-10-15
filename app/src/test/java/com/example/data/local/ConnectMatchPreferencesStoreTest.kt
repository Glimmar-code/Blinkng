package com.example.data.local

import com.example.data.models.MatchSpinPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ConnectMatchPreferencesStoreTest {
    @Test
    fun savedPreferencesSurviveRecreatingTheStoreAndStayScopedToAccount() {
        val context = RuntimeEnvironment.getApplication()
        val store = ConnectMatchPreferencesStore(context)
        val expected = MatchSpinPreferences(
            university = "Federal University of Technology Akure",
            faculty = "Engineering",
            academicLevel = "400 Level",
            connectionIntent = "Study partner",
            typePrompt = "coding and design",
            onlineOnly = true
        )
        store.save("connect-test-user-a", expected)
        assertEquals(expected, ConnectMatchPreferencesStore(context).load("connect-test-user-a"))
        assertNull(ConnectMatchPreferencesStore(context).load("connect-test-user-b"))
    }

    @Test
    fun invalidSavedJsonDoesNotCrashConnect() {
        val context = RuntimeEnvironment.getApplication()
        val shared = context.getSharedPreferences("blink_connect_match_preferences", 0)
        shared.edit().putString("preferences_corrupt-connect-user", "{not valid JSON").commit()
        assertNull(ConnectMatchPreferencesStore(context).load("corrupt-connect-user"))
        assertNull(ConnectMatchPreferencesStore(context).load(""))
    }

    @Test
    fun optionalFieldsRemainOptional() {
        val context = RuntimeEnvironment.getApplication()
        val store = ConnectMatchPreferencesStore(context)
        store.save("connect-test-user-null-fields", MatchSpinPreferences(onlineOnly = true))
        val loaded = store.load("connect-test-user-null-fields")
        assertEquals(null, loaded?.university)
        assertTrue(loaded?.onlineOnly == true)
    }
}

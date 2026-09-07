package com.blinkng.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blinkng.desktop.data.DesktopAdminCapability
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.desktop.data.DesktopSession
import com.blinkng.desktop.data.DesktopSupabaseClient
import com.blinkng.desktop.data.DesktopUserSettings

class DesktopAppState(
    val client: DesktopSupabaseClient = DesktopSupabaseClient(),
) {
    var initialized by mutableStateOf(false)
        private set
    var session by mutableStateOf<DesktopSession?>(client.session)
        private set
    var profile by mutableStateOf<DesktopProfile?>(null)
        private set
    var adminCapability by mutableStateOf(DesktopAdminCapability(false, null, false))
        private set
    var settings by mutableStateOf<DesktopUserSettings?>(null)
        private set
    var selectedRoute by mutableStateOf("home")
    var globalSearch by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    suspend fun initialize() {
        if (initialized) return
        busy = true
        try {
            session = client.restoreSession()
            if (session != null) loadAuthenticatedState()
        } catch (t: Throwable) {
            errorMessage = t.message
        } finally {
            busy = false
            initialized = true
        }
    }

    suspend fun signIn(email: String, password: String) = authAction {
        session = client.signIn(email, password)
        loadAuthenticatedState()
    }

    suspend fun signUp(email: String, password: String, username: String, fullName: String) = authAction {
        session = client.signUp(email, password, username, fullName)
        loadAuthenticatedState()
    }

    suspend fun signInWithGoogle() = authAction {
        session = client.signInWithGoogle()
        loadAuthenticatedState()
    }

    suspend fun sendPasswordReset(email: String) {
        busy = true
        errorMessage = null
        try {
            client.sendPasswordReset(email)
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Could not send the password reset email."
            throw t
        } finally {
            busy = false
        }
    }

    fun signOut() {
        client.signOut()
        session = null
        profile = null
        settings = null
        adminCapability = DesktopAdminCapability(false, null, false)
        selectedRoute = "home"
        globalSearch = ""
    }

    suspend fun refreshProfile() {
        profile = client.fetchProfile()
    }

    suspend fun updateSettings(value: DesktopUserSettings) {
        client.saveSettings(value)
        settings = value
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun loadAuthenticatedState() {
        profile = client.fetchProfile()
        settings = runCatching { client.fetchSettings() }.getOrNull()
        adminCapability = client.fetchAdminCapability()
    }

    private suspend fun authAction(block: suspend () -> Unit) {
        busy = true
        errorMessage = null
        try {
            block()
        } catch (t: Throwable) {
            errorMessage = t.message ?: "Authentication failed."
            throw t
        } finally {
            busy = false
        }
    }
}

package com.example.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.MainActivity
import com.example.data.supabase.SupabaseService

class GoogleAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent?.data)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handle(intent?.data)
    }

    private fun handle(uri: Uri?) {
        if (uri == null) { returnToMain(); return }
        val fragment = uri.fragment.orEmpty()
        val params = fragment.split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) parts[0] to Uri.decode(parts[1]) else null
        }.toMap()
        val accessToken = params["access_token"]
        val refreshToken = params["refresh_token"]
        if (!accessToken.isNullOrBlank()) SupabaseService.saveSession(accessToken, refreshToken)
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }

    private fun returnToMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

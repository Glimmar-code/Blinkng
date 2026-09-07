package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.setContent
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkTheme

class AdminControlCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        setContent {
            BlinkTheme(darkTheme = true) {
                AdminControlCenterV2 { finish() }
            }
        }
    }
}
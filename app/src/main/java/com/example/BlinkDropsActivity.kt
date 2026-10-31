package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.example.ui.screens.BlinkDropsRoute
import com.example.ui.theme.BlinkTheme

/**
 * Direct notification destination for BLINK GIVEAWAY.
 *
 * Keeping this destination separate means a foreground or background giveaway
 * notification can open the exact Giveaway dashboard without disturbing whatever
 * MainActivity navigation state the user had before.
 */
class BlinkDropsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dropId = intent?.getStringExtra(EXTRA_DROP_ID)
        setContent {
            BlinkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    BlinkDropsRoute(
                        onClose = { finish() },
                        initialDropId = dropId,
                        onGetCoins = {
                            startActivity(
                                android.content.Intent(this@BlinkDropsActivity, MainActivity::class.java)
                                    .putExtra("open_blink_coin_packs", true)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        },
                    )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DROP_ID = "blink_drop_id"
    }
}

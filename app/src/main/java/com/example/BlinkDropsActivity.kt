package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.ui.screens.BlinkDropsRoute
import com.example.ui.theme.BlinkTheme

/**
 * Direct notification destination for BLINK Drops.
 *
 * Keeping this destination separate means a foreground or background giveaway
 * notification can open the exact Drops experience without disturbing whatever
 * MainActivity navigation state the user had before.
 */
class BlinkDropsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dropId = intent?.getStringExtra(EXTRA_DROP_ID)
        setContent {
            BlinkTheme {
                Surface {
                    BlinkDropsRoute(
                        onClose = { finish() },
                        initialDropId = dropId,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_DROP_ID = "blink_drop_id"
    }
}

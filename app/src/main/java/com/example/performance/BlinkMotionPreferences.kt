package com.example.performance

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Mirrors Android's system animator-duration preference so BLINK feed chrome does not
 * force decorative motion when the user has disabled animations.
 */
@Composable
fun rememberBlinkReduceMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver

    fun readReducedMotion(): Boolean =
        runCatching {
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0f
        }.getOrDefault(false)

    var reduced by remember(resolver) { mutableStateOf(readReducedMotion()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }

    return reduced
}

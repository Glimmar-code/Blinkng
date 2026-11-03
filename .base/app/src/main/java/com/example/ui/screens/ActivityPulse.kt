package com.example.ui.screens

import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.blinkng.shared.BlinkActivityPulsePolicy
import com.blinkng.shared.BlinkPulseSessionStore
import com.blinkng.shared.nextPulseValue
import kotlinx.coroutines.delay
import kotlin.random.Random

internal data class ManagedPulseState(
    val value: Int?,
    val isStale: Boolean,
)

@Composable
internal fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
internal fun rememberWindowActive(): Boolean {
    val view = LocalView.current
    var active by remember(view) { mutableStateOf(view.hasWindowFocus()) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            active = hasFocus
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }
    }

    return active
}

@Composable
internal fun rememberManagedPulse(
    key: String,
    range: IntRange,
    tickMillis: Long,
    policy: BlinkActivityPulsePolicy,
    liveDataAvailable: Boolean,
    windowActive: Boolean,
    reduceMotion: Boolean,
): ManagedPulseState {
    val normalized = policy.normalized()
    val minValue = minOf(range.first, range.last)
    val maxValue = maxOf(range.first, range.last)
    var value by rememberSaveable(key) {
        mutableStateOf(BlinkPulseSessionStore.get(key))
    }

    LaunchedEffect(
        key,
        minValue,
        maxValue,
        tickMillis,
        normalized.minHoldMillis,
        normalized.maxStep,
        normalized.transitionStepMultiplier,
        liveDataAvailable,
        windowActive,
        reduceMotion,
    ) {
        if (!liveDataAvailable || !windowActive || !normalized.enabled) return@LaunchedEffect

        if (value == null) {
            val seeded = if (minValue == maxValue) minValue else Random.nextInt(minValue, maxValue + 1)
            value = seeded
            BlinkPulseSessionStore.put(key, seeded)
        }

        val hold = maxOf(tickMillis, normalized.minHoldMillis) * if (reduceMotion) 2L else 1L
        while (true) {
            delay(hold)
            val current = value ?: continue
            val magnitude = Random.nextInt(1, normalized.maxStep + 1)
            val direction = if (Random.nextBoolean()) 1 else -1
            var next = nextPulseValue(
                current = current,
                range = minValue..maxValue,
                requestedStep = magnitude * direction,
                maxStep = normalized.maxStep,
                transitionStepMultiplier = normalized.transitionStepMultiplier,
            )
            if (next == current && current in minValue..maxValue && minValue < maxValue) {
                next = nextPulseValue(
                    current = current,
                    range = minValue..maxValue,
                    requestedStep = if (current <= minValue) 1 else -1,
                    maxStep = normalized.maxStep,
                    transitionStepMultiplier = normalized.transitionStepMultiplier,
                )
            }
            value = next
            BlinkPulseSessionStore.put(key, next)
        }
    }

    return ManagedPulseState(
        value = value,
        isStale = !liveDataAvailable,
    )
}

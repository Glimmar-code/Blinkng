package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/** A post or reel becomes a counted view only after one continuous minute of qualified visibility. */
internal const val QUALIFIED_VIEW_DURATION_MS = 60_000L

@Composable
internal fun QualifiedViewEffect(
    contentId: String?,
    isVisible: Boolean,
    onQualified: (String) -> Unit
) {
    val latestOnQualified by rememberUpdatedState(onQualified)

    LaunchedEffect(contentId, isVisible) {
        val id = contentId?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!isVisible) return@LaunchedEffect
        delay(QUALIFIED_VIEW_DURATION_MS)
        latestOnQualified(id)
    }
}

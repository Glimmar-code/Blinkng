package com.example.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/**
 * Compatibility fallback for files that do not import Compose's transition helper.
 * The generic receiver keeps Compose's native non-generic overload preferred in
 * callers such as MainActivity that already import it explicitly.
 */
internal infix fun <T : EnterTransition> T.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)

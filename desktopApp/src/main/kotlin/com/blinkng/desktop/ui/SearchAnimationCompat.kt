package com.blinkng.desktop.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

/** Keeps search transition syntax compatible with the Compose version used by the desktop target. */
internal infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)
